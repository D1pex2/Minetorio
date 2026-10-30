local function emit(t) localised_print('MINING '..game.table_to_json(t)) end
local function stacks(inv)
 local out={}; if inv then for i=1,#inv do local st=inv[i]; if st.valid_for_read then out[#out+1]={name=st.name,count=st.count,health=st.health,type=st.type,entity_data=st.is_item_with_entity_data,color=st.is_item_with_entity_data and st.entity_color or nil,label=st.is_item_with_entity_data and st.entity_label or nil,grid_present=st.grid~=nil} end end end; return out
end
local function state(e)
 if not e or not e.valid then return nil end
 local fuel=e.type=='locomotive' and stacks(e.get_fuel_inventory()) or nil
 local burner=e.type=='locomotive' and {remaining=e.burner.remaining_burning_fuel,burning=e.burner.currently_burning and e.burner.currently_burning.name,heat=e.burner.heat} or nil
 local cargo=e.type=='cargo-wagon' and stacks(e.get_inventory(defines.inventory.cargo_wagon)) or nil
 local fluid=e.type=='fluid-wagon' and e.fluidbox[1] or nil
 return {name=e.name,health=e.health,position=e.position,speed=e.train.speed,weight=e.train.weight,color=e.color,grid_present=e.grid~=nil,fuel=fuel,burner=burner,cargo=cargo,fluid=fluid}
end
local function test(c,tick)
 local before=state(c.target); local inv=c.character.get_main_inventory()
 local ok=c.character.mine_entity(c.target,false)
 local mined=stacks(inv); local item=nil
 for i=1,#inv do if inv[i].valid_for_read and inv[i].name==c.name then item=inv[i]; break end end
 local restored=nil
 if item then restored=global.surface.create_entity{name=c.name,position={c.x,20},direction=defines.direction.north,force='player',item=item} end
 emit({kind='roundtrip',case=c.case,tick=tick,before=before,success=ok,target_valid=c.target.valid,items=mined,source_stack_found=item~=nil,restored=state(restored)})
 c.done=true
end
script.on_init(function()
 local s=game.create_surface('mining5',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}}); global.surface=s
 for x=0,96,32 do for y=-256,64,32 do s.request_to_generate_chunks({x,y},0) end end; s.force_generate_chunk_requests(); global.cases={}
 for i=1,4 do
  local x=i*16
  for y=-220,40,2 do s.create_entity{name='straight-rail',position={x,y},direction=defines.direction.north,force='player'} end
  local names={'locomotive','cargo-wagon','fluid-wagon','locomotive'}; local name=names[i]
  local e=s.create_entity{name=name,position={x,0},direction=defines.direction.north,force='player'}
  local ch=s.create_entity{name='character',position={x+4,8},force='player'}
  if not e or not ch then emit({kind='setup_error',case=i}); return end
  e.color={r=0.2,g=0.4,b=0.6,a=1}
  if i==1 or i==4 then e.get_fuel_inventory().insert{name='coal',count=7} end
  if i==2 then e.get_inventory(defines.inventory.cargo_wagon).insert{name='iron-plate',count=30}; e.health=275 end
  if i==3 then e.fluidbox[1]={name='water',amount=1234,temperature=42}; e.health=300 end
  local a=nil; if i==4 then a=s.create_entity{name='train-stop',position={x+2,-200},direction=defines.direction.north,force='player'}; a.backer_name='Warm' end
  global.cases[i]={case=({'cold_loco','cargo_damaged','fluid_damaged','warm_loco'})[i],name=name,x=x,target=e,character=ch,station=a}
  emit({kind='setup',case=i,target=state(e)})
 end
end)
script.on_event(defines.events.on_tick,function(e)
 local c=global.cases
 if e.tick==0 and c[4] then local t=c[4].target.train; t.schedule={current=1,records={{station='Warm',wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; t.manual_mode=false end
 if e.tick==1 then for i=1,3 do if c[i] then test(c[i],e.tick) end end end
 if e.tick==60 and c[4] and c[4].target.valid then c[4].target.train.manual_mode=true end
 if e.tick>=61 and e.tick<=250 and c[4] and not c[4].done and c[4].target.valid and c[4].target.train.speed==0 then test(c[4],e.tick) end
end)
